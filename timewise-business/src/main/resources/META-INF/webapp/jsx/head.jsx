import React from "react";

const Head = function (props) {
    let title;
    return (
        <head>
            <title>{props.title ? props.title : 'Timewise'}</title>
            <link rel="stylesheet" type="text/css" href="/css/normalize.css" />
            <link rel="stylesheet" type="text/css" href="/css/main.css" />
        </head>);
};

export default Head;