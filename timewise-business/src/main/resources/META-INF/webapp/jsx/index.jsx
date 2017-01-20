import React from 'react';
import ReactDOMServer from 'react-dom/server';
import Head from './head.jsx';
import TopBar from './topBar.jsx';
import Content from './content.jsx';

let body = (<body>
    <div id="container">
        <TopBar/>
        <Content path="landing"/>
    </div>
</body>);

var output = '<html>';
output += ReactDOMServer.renderToStaticMarkup((<Head title="Timewise"/>));
output += ReactDOMServer.renderToString(body);
output += '</html>';

export default output;

