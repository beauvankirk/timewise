import React from "react";
import ReactDOMServer from "react-dom/server";
import Head from "./server/head.jsx";
import {createStore} from "redux";
import {Provider} from "react-redux";
import reducer from "./reducers";
import App from "./app.jsx";

let store;
if (typeof state === 'undefined' || !state) {
    store = createStore(reducer);
} else {
    store = createStore(reducer, state);
}



const head = ReactDOMServer.renderToStaticMarkup(
    (
        <Provider store={store}>
            <Head title="Timewise"/>
        </Provider>
    )
);

const html = ReactDOMServer.renderToString(
    (
        <Provider store={store}>
            <App/>
        </Provider>
    )
);

function renderFullPage(head, html, preloadedState) {
    return `
    <!doctype html>
    <html>
      ${head}
      <body>
        <div id="root">${html}</div>
        <script>
          // WARNING: See the following for Security isues with this approach:
          // http://redux.js.org/docs/recipes/ServerRendering.html#security-considerations
          window.__PRELOADED_STATE__ = ${JSON.stringify(preloadedState)}
        </script>
        <script src="/jsx/client/client.jsx"></script>
      </body>
    </html>
    `
}

export default renderFullPage(head, html, store.getState());
