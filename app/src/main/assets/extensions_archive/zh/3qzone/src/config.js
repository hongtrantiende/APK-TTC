let BASE_URL = 'https://www.3qzone.io';
try {
    if (CONFIG_URL) {
        BASE_URL = CONFIG_URL;
    }
} catch (error) {
}