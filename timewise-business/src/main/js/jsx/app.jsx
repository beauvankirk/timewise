import React from "react";
import TopBar from "./topBar.jsx";
import Content from "./content.jsx";

export default function (props) {
    return (
        <div id="container">
            <TopBar/>
            <Content path="landing"/>
        </div>)
};
