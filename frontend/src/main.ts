// sockjs-client references the Node global `global`, which doesn't exist in the browser.
// Alias it to the window before anything imports SockJS, or the app crashes at load.
(globalThis as any).global = globalThis;

import { bootstrapApplication } from '@angular/platform-browser';
import { AppComponent } from './app/app.component';
import { appConfig } from './app/app.config';

bootstrapApplication(AppComponent, appConfig).catch((err) => console.error(err));
