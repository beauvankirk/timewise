import React from 'react'

const loggedIn = function(props) {
  return <span>{props.user.name}</span>
};

export default class User extends React.Component {

    // constructor(props) {
    //     super(props);
    //     this.props = props;
    // }
    render() {
        return <span>{props.user ? loggedIn(props) : <a href="#">Log in</a>}</span>
    }
}