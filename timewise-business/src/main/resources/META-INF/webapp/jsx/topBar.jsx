import React from "react";
import User from './user.jsx';
import Logo from './logo.jsx';

const bar = function (props) {
    return (
        <div id="TopBar">
            <Logo size="small" />
            <User user="asdf"/>
        </div>);
};

export default bar;