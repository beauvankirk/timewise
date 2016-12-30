import Head from './head.jsx';


var MyComponent = React.createClass({
    render: function(){
        return (
            <h1>Hello, world!</h1>
        );
    }
});


var body = (<body>
    <div id="myDiv">
        <MyComponent/>
    </div>
</body>);

var output = '<html>';
output += ReactDOMServer.renderToStaticMarkup(Head);
output += ReactDOMServer.renderToString(body);
output += '</html>';

export default output;

