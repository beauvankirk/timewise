import React from "react";

export default class Logo extends React.Component {

    // constructor(props) {
    //     super(props);
    //     this.props = props;
    // }
    render() {
        return <div className={this.props.size ? this.props.size + " logo" : "logo"}>
                <img src="http://lorempixel.com/100/50/" width={100} height={50} />
            </div>
    }
}