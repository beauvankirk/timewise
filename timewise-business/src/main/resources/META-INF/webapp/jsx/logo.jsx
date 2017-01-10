import React from "react";

export default class Logo extends React.Component {

    render() {
        return <div className={this.props.size ? this.props.size + " logo" : "logo"}>
                <img src="/img/logo_withtext_100px.png" width="auto" height={50} />
            </div>
    }
}