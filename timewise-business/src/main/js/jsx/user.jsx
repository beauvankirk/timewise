import React from 'react'

const loggedIn = function(props) {
  return <span>{props.loggedIn && props.user ? props.user.name : <a href="#">Log in</a>}</span>
};

export default class User extends React.Component {

    // constructor(props) {
    //     super(props);
    //     this.props = props;
    // }
    render() {
        return <span className="userbox">{loggedIn(this.props)}</span>
    }
}