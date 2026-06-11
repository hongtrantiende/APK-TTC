load('config.js');
function execute() {
    return Response.success([
        // --- GENRES ---
        {title: "Action", input: "https://www.inkitt.com/genres/action", script: "gen.js"},
        {title: "Adventure", input: "https://www.inkitt.com/genres/adventure", script: "gen.js"},
        {title: "Drama", input: "https://www.inkitt.com/genres/drama", script: "gen.js"},
        {title: "Erotica", input: "https://www.inkitt.com/genres/erotica", script: "gen.js"},
        {title: "Fantasy", input: "https://www.inkitt.com/genres/fantasy", script: "gen.js"},
        {title: "Historical Fiction", input: "https://www.inkitt.com/genres/historical-fiction", script: "gen.js"},
        {title: "Horror", input: "https://www.inkitt.com/genres/horror", script: "gen.js"},
        {title: "Humor", input: "https://www.inkitt.com/genres/humor", script: "gen.js"},
        {title: "LGBTQ+", input: "https://www.inkitt.com/genres/lgbtq", script: "gen.js"},
        {title: "Literary Fiction", input: "https://www.inkitt.com/genres/literary-fiction", script: "gen.js"},
        {title: "Mystery", input: "https://www.inkitt.com/genres/mystery", script: "gen.js"},
        {title: "Other", input: "https://www.inkitt.com/genres/other", script: "gen.js"},
        {title: "Poetry", input: "https://www.inkitt.com/genres/poetry", script: "gen.js"},
        {title: "Romance", input: "https://www.inkitt.com/genres/romance", script: "gen.js"},
        {title: "Scifi", input: "https://www.inkitt.com/genres/scifi", script: "gen.js"},
        {title: "Thriller", input: "https://www.inkitt.com/genres/thriller", script: "gen.js"},
        {title: "Young Adult", input: "https://www.inkitt.com/genres/young-adult", script: "gen.js"},

        // --- FANFICTION ---
        {title: "Fanfiction: Asian Pop", input: "https://www.inkitt.com/fanfiction/asian-pop", script: "gen.js"},
        {title: "Fanfiction: DC Universe", input: "https://www.inkitt.com/fanfiction/dc-universe", script: "gen.js"},
        {title: "Fanfiction: Marvel Universe", input: "https://www.inkitt.com/fanfiction/marvel-universe", script: "gen.js"},
        {title: "Fanfiction: Star Wars", input: "https://www.inkitt.com/fanfiction/star-wars", script: "gen.js"},
        {title: "Fanfiction: Harry Potter", input: "https://www.inkitt.com/fanfiction/harry-potter", script: "gen.js"},

        // --- TRENDING TOPICS ---
        {title: "Topic: Sex", input: "https://www.inkitt.com/topics/sex", script: "gen.js"},
        {title: "Topic: Love", input: "https://www.inkitt.com/topics/love", script: "gen.js"},
        {title: "Topic: Erotic", input: "https://www.inkitt.com/topics/erotic", script: "gen.js"},
        {title: "Topic: Love Story", input: "https://www.inkitt.com/topics/love-story", script: "gen.js"},
        {title: "Topic: Dark", input: "https://www.inkitt.com/topics/dark", script: "gen.js"},
        {title: "Topic: Werewolf", input: "https://www.inkitt.com/topics/werewolf", script: "gen.js"},
        {title: "Topic: Romance/Drama", input: "https://www.inkitt.com/topics/romance-drama", script: "gen.js"},
        {title: "Topic: Mature", input: "https://www.inkitt.com/topics/mature", script: "gen.js"},
        {title: "Topic: Supernatural", input: "https://www.inkitt.com/topics/supernatural", script: "gen.js"},
        {title: "Topic: Betrayal", input: "https://www.inkitt.com/topics/betrayal", script: "gen.js"},
        {title: "Topic: Secrets", input: "https://www.inkitt.com/topics/secrets", script: "gen.js"},
        {title: "Topic: Alpha", input: "https://www.inkitt.com/topics/alpha", script: "gen.js"}
    ]);
}