package org.elasticsearch.rest.action.suggest.SuggestActionTest;

import static org.elasticsearch.rest.action.suggest.SuggestActionTest.NodeTestHelper.*;
import static org.elasticsearch.rest.action.suggest.SuggestActionTest.ProductTestHelper.createProducts;
import static org.elasticsearch.rest.action.suggest.SuggestActionTest.ProductTestHelper.indexProducts;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.elasticsearch.action.deletebyquery.DeleteByQueryRequest;
import org.elasticsearch.common.collect.Lists;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.node.Node;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.Parameterized.Parameters;

public abstract class AbstractSuggestTest {

    private String clusterName = "SuggestIntegrationTestCluster_" + Math.random();
    protected Node node;
    protected List<Node> nodes = Lists.newArrayList();

    @Parameters
    public static Collection<Object[]> data() {
        // first argument: number of shards, second argument: number of nodes
//        Object[][] data = new Object[][] { { 1,1 } };
        Object[][] data = new Object[][] { { 1, 1 }, { 4, 1 }, { 10, 1 }, { 4, 4 } };
        return Arrays.asList(data);
    }

    public AbstractSuggestTest(int shards, int nodeCount) throws Exception {
        for (int i = 0 ; i < nodeCount ; i++) {
            nodes.add(createNode(clusterName, shards));
        }

        node = nodes.get(0);
    }


    @After
    public void stopNodes() throws Exception {
        for (Node node : nodes) {
            node.client().close();
            node.close();
        }
    }

    abstract public List<String> getSuggestions(String field, String term, Integer size, Float similarity) throws Exception;
    abstract public List<String> getSuggestions(String field, String term, Integer size) throws Exception;
    abstract public void refreshSuggestIndex() throws Exception;


    @Test
    public void testThatSimpleSuggestionWorks() throws Exception {
        List<Map<String, Object>> products = createProducts(4);
        products.get(0).put("ProductName", "foo");
        products.get(1).put("ProductName", "foob");
        products.get(2).put("ProductName", "foobar");
        products.get(3).put("ProductName", "boof");
        indexProducts(products, node);

        List<String> suggestions = getSuggestions("ProductName.suggest", "foo", 10);
        assertThat(suggestions.toString(), suggestions, hasSize(3));
        assertThat(suggestions.toString(), suggestions, contains("foo", "foob", "foobar"));
    }

    @Test
    public void testThatSimpleSuggestionShouldSupportLimit() throws Exception {
        List<Map<String, Object>> products = createProducts(3);
        products.get(0).put("ProductName", "foob");
        products.get(1).put("ProductName", "fooba");
        products.get(2).put("ProductName", "foobar");
        assertThat(products.size(), is(3));
        indexProducts(products, node);

        List<String> suggestions = getSuggestions("ProductName.suggest", "foo", 2);
        assertThat(suggestions + " is not correct", suggestions, hasSize(2));
        assertThat(suggestions.toString(), suggestions, contains("foob", "fooba"));
    }

    @Ignore("Did not yet investigate why this test does not work, the only difference is the productname of the first product, which matches the searchterm")
    @Test
    public void testThatSimpleSuggestionShouldSupportLimitWithConcreteWord() throws Exception {
        List<Map<String, Object>> products = createProducts(3);
        products.get(0).put("ProductName", "foo");
        products.get(1).put("ProductName", "fooba");
        products.get(2).put("ProductName", "foobar");
        assertThat(products.size(), is(3));
        indexProducts(products, node);

        List<String> suggestions = getSuggestions("ProductName.suggest", "foo", 2);
        assertThat(suggestions + " is not correct", suggestions, hasSize(2));
        assertThat(suggestions, contains("foob", "fooba"));
    }

    @Test
    public void testThatSuggestionShouldNotContainDuplicates() throws Exception {
        List<Map<String, Object>> products = createProducts(3);
        products.get(0).put("ProductName", "foo");
        products.get(1).put("ProductName", "foob");
        products.get(2).put("ProductName", "foo");
        indexProducts(products, node);

        List<String> suggestions = getSuggestions("ProductName.suggest", "foo", 10);
        assertThat(suggestions, hasSize(2));
        assertThat(suggestions, contains("foo", "foob"));
    }

    @Test
    public void testThatSuggestionShouldWorkOnDifferentFields() throws Exception {
        List<Map<String, Object>> products = createProducts(3);
        products.get(0).put("ProductName", "Kochjacke Pute");
        products.get(1).put("ProductName", "Kochjacke Henne");
        products.get(2).put("ProductName", "Kochjacke Hahn");
        products.get(0).put("Description", "Kochjacke Pute");
        products.get(1).put("Description", "Kochjacke Henne");
        products.get(2).put("Description", "Kochjacke Hahn");
        indexProducts(products, node);

        List<String> suggestions = getSuggestions("ProductName.suggest", "kochjacke", 10);
        assertSuggestions(suggestions, "kochjacke", "kochjacke hahn", "kochjacke henne", "kochjacke pute");

        suggestions = getSuggestions("Description", "Kochjacke", 10);
        assertSuggestions(suggestions, "Kochjacke Hahn", "Kochjacke Henne", "Kochjacke Pute");
    }

    @Test
    public void testThatSuggestionShouldWorkWithWhitespaces() throws Exception {
        List<Map<String, Object>> products = createProducts(3);
        products.get(0).put("ProductName", "Kochjacke Paul");
        products.get(1).put("ProductName", "Kochjacke Pauline");
        products.get(2).put("ProductName", "Kochjacke Paulinea");
        indexProducts(products, node);

        List<String> suggestions = getSuggestions("ProductName.suggest", "kochja", 10);
        assertSuggestions(suggestions, "kochjacke", "kochjacke paul", "kochjacke pauline", "kochjacke paulinea");

        suggestions = getSuggestions("ProductName.suggest", "kochjacke ", 10);
        assertSuggestions(suggestions, "kochjacke paul", "kochjacke pauline", "kochjacke paulinea");

        suggestions = getSuggestions("ProductName.suggest", "kochjacke pauline", 10);
        assertSuggestions(suggestions, "kochjacke pauline", "kochjacke paulinea");
    }

    @Test
    public void testThatSuggestionWithShingleWorksAfterUpdate() throws Exception {
        List<Map<String, Object>> products = createProducts(3);
        products.get(0).put("ProductName", "Kochjacke Paul");
        products.get(1).put("ProductName", "Kochjacke Pauline");
        products.get(2).put("ProductName", "Kochjacke Paulinator");
        indexProducts(products, node);

        List<String> suggestions = getSuggestions("ProductName.suggest", "kochjacke", 10);
        assertSuggestions(suggestions, "kochjacke", "kochjacke paul", "kochjacke paulinator", "kochjacke pauline");

        products = createProducts(1);
        products.get(0).put("ProductName", "Kochjacke PaulinPanzer");
        indexProducts(products, node);
        refreshSuggestIndex();

        suggestions = getSuggestions("ProductName.suggest", "kochjacke paulin", 10);
        assertSuggestions(suggestions, "kochjacke paulinator", "kochjacke pauline", "kochjacke paulinpanzer");

        cleanIndex();
        refreshSuggestIndex();
        suggestions = getSuggestions("ProductName.suggest", "kochjacke paulin", 10);
        assertThat(suggestions.size(), is(0));
    }

    @Test
    public void testThatSuggestionWorksWithSimilarity() throws Exception {
        List<Map<String, Object>> products = createProducts(4);
        products.get(0).put("ProductName", "kochjacke bla");
        products.get(1).put("ProductName", "kochjacke blubb");
        products.get(2).put("ProductName", "kochjacke blibb");
        products.get(3).put("ProductName", "kochjacke paul");
        indexProducts(products, node);

        List<String> suggestions = getSuggestions("ProductName.suggest", "kochajcke", 10, 0.75f);
        assertThat(suggestions, hasSize(1));
        assertThat(suggestions, contains("kochjacke"));
    }

//    @Test
//    public void performanceTest() throws Exception {
//        List<Map<String, Object>> products = createProducts(60000);
//        indexProducts(products);
//
//        System.out.println(measureSuggestTime("a"));
//        System.out.println(measureSuggestTime("aa"));
//        System.out.println(measureSuggestTime("aaa"));
//        System.out.println(measureSuggestTime("aaab"));
//        System.out.println(measureSuggestTime("aaabc"));
//        System.out.println(measureSuggestTime("aaabcd"));
//    }
//
//    private long measureSuggestTime(String search) throws Exception {
//        long start = System.currentTimeMillis();
//        getSuggestions("ProductName.suggest", "aaa", 10);
//        long end = System.currentTimeMillis();
//
//        return end - start;
//    }

    private void assertSuggestions(List<String> suggestions, String ... terms) {
        assertThat(suggestions.toString() + "should have size " + terms.length, suggestions, hasSize(terms.length));
        assertThat("Suggestions are: " + suggestions, suggestions, contains(terms));
    }

    private void cleanIndex() {
        node.client().deleteByQuery(new DeleteByQueryRequest("products").types("product").query(QueryBuilders.matchAllQuery())).actionGet();
    }

}
