/**
 * Created by erikhakansson on 2016-12-27.
 */
function(stringBuilder, realReactDOMServer) {
    this.renderToString = function (reactObject) {
        stringBuilder.append(realReactDOMServer.renderToString(reactObject));
    };
    this.renderToStaticMarkup = function (reactObject) {
        stringBuilder.append(realReactDOMServer.renderToStaticMarkup(reactObject));
    };
    this.version = realReactDOMServer.version;
}
