let BASE_URL = 'https://novel-api.elklk.cn';
try {
    if (CONFIG_URL) {
        BASE_URL = CONFIG_URL;
    }
} catch (error) {
}