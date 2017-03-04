import React from "react";
import Modal from "react-modal";
import axios from 'axios';

export default class User extends React.Component {
    constructor() {
        super();

        this.state = {
            modalIsOpen: false
        };

        this.openModal = this.openModal.bind(this);
        this.closeModal = this.closeModal.bind(this);
    }

    openModal() {
        this.setState({modalIsOpen: true});
    }

    closeModal() {
        this.setState({modalIsOpen: false});
    }

    postLogin(event) {
        event.preventDefault();
        const formData = Array.from(event.target.elements)
            .filter(el => el.name)
            .reduce((a, b) => ({...a, [b.name]: b.value}), {});
        console.dir(formData);
        axios.post('/rest/login', formData).then(function (response) {
            console.log(response);
        }).catch(function(error) {
            console.error(error);
        });
        return false;
    }

    render() {
        return (
            <span>
                <Modal
                    isOpen={this.state.modalIsOpen}
                    contentLabel="Modal"
                >
                    <h1>Log in</h1>

                        <form onSubmit={this.postLogin} >
                            <input name="email" type="email" placeholder="E-mail" required="true" />
                            <input name="password" type="password" required="true" />

                            <button type="submit">Log in</button>
                        </form>
                    <button onClick={this.closeModal}>Abort</button>
                </Modal>
                <span className="userbox">
                    <span>{this.props.loggedIn && this.props.user ? this.props.user.name :
                        <a href="#" onClick={this.openModal}>Log in</a>}</span>
                </span>
            </span>
        )
    }
}
