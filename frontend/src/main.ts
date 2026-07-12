// NOTE: the `global` alias sockjs-client needs is set in index.html's <head>, before any bundle JS
// loads — it cannot live here because ES import hoisting evaluates the sockjs import graph first.
import { bootstrapApplication } from '@angular/platform-browser';
import { AppComponent } from './app/app.component';
import { appConfig } from './app/app.config';

bootstrapApplication(AppComponent, appConfig).catch((err) => console.error(err));
