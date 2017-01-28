import React from "react";
import { connect } from 'react-redux'


class Head extends React.Component {
    render() {
        return (
            <head>
                <meta name="test" content={this.props.asdf} />
                <title>{this.props.title ? this.props.title : 'Timewise'}</title>
                <link rel="stylesheet" type="text/css" href="/css/normalize.css" />
                <link rel="stylesheet" type="text/css" href="/css/main.css" />
            </head>);
    }
}

const mapStateToProps = (state, ownProps) => {
    console.log(state);
    return {
        asdf: state.hasOwnProperty('asdf') ? state.asdf : 'noope'
    }
};

const mapDispatchToProps = (dispatch, ownProps) => {
    return {
        onClick: () => {
            dispatch(setVisibilityFilter(ownProps.filter))
        }
    }
};

export default connect(mapStateToProps, null)(Head)