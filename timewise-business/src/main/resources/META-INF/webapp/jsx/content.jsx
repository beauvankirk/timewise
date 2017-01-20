import React from "react";

export default class Content extends React.Component {

    render() {
        return <div id="Content" className={"content content-" + this.props.path}>
            {this.getLanding()}
            </div>
    }

    getLanding() {
        return <div>
            <p><img src="/img/logo_withtext_200px.png" width="auto" height={200} /></p>
            <p style={{textAlign: "center"}}>Welcome to Timewise. Please <a href="#">log in</a> to continue</p>
        </div>
    }
}