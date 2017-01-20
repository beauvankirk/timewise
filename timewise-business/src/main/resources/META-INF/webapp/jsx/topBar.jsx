import React from "react";
import User from './user.jsx';
import Logo from './logo.jsx';

const bar = function (props) {
    return (
        <div id="TopBar">
            <div id="TopBarContent">
                <Logo size="small" />
                <User user="asdf"/>
            </div>
            <div id="TopBarPadding">&nbsp;</div>
        </div>
    );
};

export default bar;