updatePageHtml();

function translatePage() {
    window.vBook.translatePage(document.getElementsByTagName('html')[0].innerHTML);
}

function updatePageHtml() {
    window.vBook.onContentLoaded(document.getElementsByTagName('html')[0].innerHTML);
}

function setRawContent() {
    document.getElementsByTagName('html')[0].innerHTML = window.vBook.getRawContent();
}

function setGoogleTranslateContent() {
    document.getElementsByTagName('html')[0].innerHTML = window.vBook.getGoogleTranslateContent();
}

function setVPTranslateContent() {
    document.getElementsByTagName('html')[0].innerHTML = window.vBook.getVPTranslateContent();
}

function setHanVietTranslateContent() {
    document.getElementsByTagName('html')[0].innerHTML = window.vBook.getHanVietTranslateContent();
}