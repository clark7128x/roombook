"use strict";
htmx.config.allowEval = false;
htmx.config.includeIndicatorStyles = false;
document.addEventListener("htmx:beforeSwap", function (event) {
    const status = event.detail.xhr.status;
    if (status >= 400 && status !== 401) {
        event.detail.shouldSwap = true;
        event.detail.isError = false;
    }
});
