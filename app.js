const createError = require('http-errors');
const express = require('express');
const path = require('node:path');
const cookieParser = require('cookie-parser');
const logger = require('morgan');
const bodyParser = require('body-parser');
const indexRouter = require('./routes/controller/index');
const notificationsRouter = require('./routes/controller/notification');


const app = express();

// view engine setup
app.set('views', path.join(__dirname, 'views'));
app.set('view engine', 'hjs');

app.use(logger('dev'));
// app.use(express.json({limit: "500mb"}));
app.use(bodyParser.json());
app.use(express.text());
app.use(express.urlencoded({ extended: true, limit: "500mb" }));
app.use(cookieParser());
app.use(express.static(path.join(__dirname, 'public')));

app.use((req, res, next) => {
  // no cache
  res.header("Cache-Control", "no-cache, no-store, must-revalidate");
  res.header("Pragma", "no-cache");

  // set powered by
  res.header("X-Powered-By", "livteam");
  next();
});

app.use('/', indexRouter);
app.use('/notification', notificationsRouter);

// catch 404 and forward to error handler
app.use(function(req, res, next) {
  next(createError(404));
});

// error handler
app.use(function(err, req, res, next) {
  // set locals, only providing error in development
  res.locals.message = err.message;
  res.locals.error = req.app.get('env') === 'development' ? err : {};

  // render the error page
  res.status(err.status || 500);
  res.render('error');
});

module.exports = app;
