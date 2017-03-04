import { combineReducers } from 'redux'

const asdfReducer = (state = 'not set', action) => {

    //For now, just return state
    return state;
};

export default combineReducers({asdf: asdfReducer});
