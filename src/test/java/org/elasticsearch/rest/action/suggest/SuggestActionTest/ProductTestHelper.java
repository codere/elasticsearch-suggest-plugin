package org.elasticsearch.rest.action.suggest.SuggestActionTest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.common.RandomStringGenerator;
import org.elasticsearch.common.collect.Lists;
import org.elasticsearch.common.collect.Maps;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.node.Node;

public class ProductTestHelper {

    static void indexProducts(List<Map<String, Object>> products, Node node) throws Exception {
        long currentCount = getCurrentDocumentCount("products", node);
        BulkRequest bulkRequest = new BulkRequest();
        for (Map<String, Object> product : products) {
            IndexRequest indexRequest = new IndexRequest("products", "product", (String)product.get("ProductId"));
            indexRequest.source(product);
            bulkRequest.add(indexRequest);
        }
        BulkResponse response = node.client().bulk(bulkRequest).actionGet();
        if (response.hasFailures()) {
            fail("Error in creating products: " + response.buildFailureMessage());
        }

        refreshIndex("products", node);
        assertDocumentCountAfterIndexing("products", products.size() + currentCount, node);
    }

    public static List<Map<String, Object>> createProducts(int count) throws Exception {
        List<Map<String, Object>> products = Lists.newArrayList();

        for (int i = 0 ; i < count; i++) {
            Map<String, Object> product = Maps.newHashMap();
            product.put("ProductName", RandomStringGenerator.randomAlphabetic(10));
            product.put("ProductId", i + "_" + RandomStringGenerator.randomAlphanumeric(10));
            products.add(product);
        }

        return products;
    }

    public static void refreshIndex(String index, Node node) throws ExecutionException, InterruptedException {
        node.client().admin().indices().refresh(new RefreshRequest("products")).get();
    }

    public static void assertDocumentCountAfterIndexing(String index, long expectedDocumentCount, Node node) throws Exception {
        assertThat(getCurrentDocumentCount(index, node), is(expectedDocumentCount));
    }

    public static long getCurrentDocumentCount(String index, Node node) {
        return node.client().prepareCount(index).setQuery(QueryBuilders.matchAllQuery()).execute().actionGet(2000).count();
    }

}
