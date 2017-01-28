import React from "react";
import {render} from "react-dom";
import {createStore} from "redux";
import {Provider} from "react-redux";
import App from "./app.jsx";
import reducer from "./reducers";

const preloadedState = window.__PRELOADED_STATE__;

const store = createStore(reducer, preloadedState);

render(
    <Provider store={store}>
        <App/>
    </Provider>,
    document.getElementById('root'));