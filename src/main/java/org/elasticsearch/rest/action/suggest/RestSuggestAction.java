package org.elasticsearch.rest.action.suggest;

import static org.elasticsearch.rest.RestRequest.Method.POST;
import static org.elasticsearch.rest.RestStatus.OK;
import static org.elasticsearch.rest.action.support.RestActions.buildBroadcastShardsHeader;

import java.io.IOException;
import java.util.Map;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.suggest.SuggestAction;
import org.elasticsearch.action.suggest.SuggestRequest;
import org.elasticsearch.action.suggest.SuggestResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClientNodesService;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.support.XContentMapValues;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.XContentRestResponse;
import org.elasticsearch.rest.XContentThrowableRestResponse;
import org.elasticsearch.rest.action.support.RestActions;
import org.elasticsearch.rest.action.support.RestXContentBuilder;
import org.elasticsearch.service.suggest.SuggestService;

public class RestSuggestAction extends BaseRestHandler {

    @Inject SuggestService suggestService;
    @Inject TransportClientNodesService nodesService;

    @Inject public RestSuggestAction(Settings settings, Client client, RestController controller) {
        super(settings, client);
        controller.registerHandler(POST, "/{index}/{type}/_suggest", this);
    }

    public void handleRequest(final RestRequest request, final RestChannel channel) {
        final String[] indices = RestActions.splitIndices(request.param("index"));

        try {
            XContentParser parser = XContentFactory.xContent(request.contentByteArray()).createParser(request.contentByteArray());
            Map<String, Object> parserMap = parser.mapAndClose();

            SuggestRequest suggestRequest = new SuggestRequest(indices);
            suggestRequest.field(XContentMapValues.nodeStringValue(parserMap.get("field"), ""));
            suggestRequest.term(XContentMapValues.nodeStringValue(parserMap.get("term"), ""));
            suggestRequest.similarity(XContentMapValues.nodeFloatValue(parserMap.get("similarity"), 1.0f));
            suggestRequest.size(XContentMapValues.nodeIntegerValue(parserMap.get("size"), 10));

            client.execute(SuggestAction.INSTANCE, suggestRequest, new ActionListener<SuggestResponse>() {
                public void onResponse(SuggestResponse response) {
                    try {
                        XContentBuilder builder = RestXContentBuilder.restContentBuilder(request);
                        builder.startObject();
                        builder.field("suggestions", response.suggestions());
                        buildBroadcastShardsHeader(builder, response);

                        builder.endObject();
                        channel.sendResponse(new XContentRestResponse(request, OK, builder));
                    } catch (Exception e) {
                        onFailure(e);
                    }
                }

                public void onFailure(Throwable e) {
                    try {
                        channel.sendResponse(new XContentThrowableRestResponse(request, e));
                    } catch (IOException e1) {
                        logger.error("Failed to send failure response", e1);
                    }
                }

            });

        } catch (Exception e) {
            try {
                channel.sendResponse(new XContentThrowableRestResponse(request, e));
            } catch (IOException e1) {
                logger.error("Failed to send failure response", e1);
            }
        }

    }
}
