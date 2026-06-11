let BASE_URL = 'http://www.xiaoshubao.net';
let BASE_MOBILE = 'http://m.xiaoshubao.net'
try {
    if (CONFIG_URL) {
        BASE_URL = CONFIG_URL;
        BASE_MOBILE = CONFIG_URL;
    }
} catch (error) {
}