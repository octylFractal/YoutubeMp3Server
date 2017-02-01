let $statusText;
let $downloadBox;
let $errorDisplay;
let $progressBox;
let $rotateArrow;

let activeSse;

function initializeTargets() {
    $statusText = $("#statusText");
    $progressBox = $("#progress-box");
    $downloadBox = $("#download-box");
    $errorDisplay = $("#error-display");
    $rotateArrow = $("#actual-arrow-holder");
}

function emptyData() {
    [$statusText, $statusText, $downloadBox, $errorDisplay, $progressBox].forEach(
        jq => jq.empty()
    );
}

function tearDownSse() {
    if (activeSse !== undefined) {
        activeSse.close();
        activeSse = undefined;
    }
}

const Status = {
    CREATED: 'CREATED',
    CONVERTING: 'CONVERTING',
    SUCCESSFUL: 'SUCCESSFUL',
    FAILED: 'FAILED'
};

function setupSse(id) {
    tearDownSse();
    const source = new EventSource(`/mp3ify/${id}/stream`);
    source.addEventListener("status", e => {
        const status = e.data;
        if (status === Status.FAILED) {
            $statusText.text("Failed!");
            $.get(`/mp3ify/${id}/status`, data => $errorDisplay.text(data.reason));
        } else if (status === Status.SUCCESSFUL) {
            $statusText.text("Conversion complete!");
            onSuccess(id);
        } else if (status == Status.CONVERTING) {
            $statusText.text("Converting...");
        }
    });
    source.addEventListener("outputLine", e => {
        $progressBox.append(e.data + '\n');
    });
    activeSse = source;
}

function onSuccess(id) {
    $.get(`/mp3ify/${id}/fileName`, fileName => {
        $downloadBox['html'](`<a href="/mp3ify/${id}/download">Download ${fileName}!</a>`);
    });
}

function rotateArrow() {
    $rotateArrow.toggleClass("rotate-arrow");
}

$(() => {
    initializeTargets();
    const $videoForm = $("#video-form");
    $videoForm.submit(e => {
        rotateArrow();
        e.preventDefault();

        const video = $("#video").val();

        emptyData();
        $['post']("/mp3ify", {
            'video': video
        })['done'](id => {
            console.log(`Converting ${video}, job ID = ${id}`);
            $statusText.text(`Converting...`);
            setupSse(id);
        })['fail']((jqXHR, textStatus, error) => {
            $statusText.text(`Error: ${error}`)
        })
    });
});