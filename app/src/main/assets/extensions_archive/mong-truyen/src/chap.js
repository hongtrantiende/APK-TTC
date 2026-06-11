function execute(url) {
    // 1. Tải trang chương để lấy id, nonce và token
    let response = fetch(url);
    
    if (response.ok) {
        let doc = response.html();
        let contentTag = doc.select("#noi_dung_truyen");
        
        let id_truyen = contentTag.attr("data-mt-id-truyen");
        let id_chapter = contentTag.attr("data-mt-id-chapter");
        let nonce = contentTag.attr("data-mt-nonce");
        let pnvn_token = doc.select("input[name='pnvn_token']").attr("value");

        // 2. Gọi Ajax POST lấy nội dung
        let ajaxRes = fetch("https://mongtruyen.com/sources/ajax/load-chapter-content.php", {
            method: "POST",
            headers: {
                "content-type": "application/x-www-form-urlencoded; charset=UTF-8",
                "x-requested-with": "XMLHttpRequest"
            },
            body: {
                "pnvn_token": pnvn_token,
                "id_truyen": id_truyen,
                "id_chapter": id_chapter,
                "nonce": nonce
            }
        });

        if (ajaxRes.ok) {
            let resJson = ajaxRes.json();
            
            // 3. Kiểm tra nếu status là success và có nội dung
            if (resJson && resJson.status === "success" && resJson.noi_dung) {
                let htmlContent = resJson.noi_dung;

                // Làm sạch nội dung
                let cleanDoc = Html.parse(htmlContent);
                cleanDoc.select(".mt-hidden-watermark").remove();

                let content = cleanDoc.html()
                    .replace(/&lt;\/p&gt;/g, "\n\n")
                    .replace(/&lt;p&gt;/g, "")
                    .replace(/<br\s*\/?>/g, "\n")
                    .replace(/<[^>]*>/g, "")
                    .replace(/\n{3,}/g, "\n\n")
                    .trim();

                // Nếu sau khi làm sạch mà vẫn rỗng (trường hợp hiếm)
                if (content.length < 10) {
                    return Response.success("Chương Vip không lấy được nội dung");
                }

                return Response.success(content);
            }
        }
        
        // 4. Trả về thông báo nếu Ajax không thành công hoặc status không phải success
        return Response.success("Chương Vip không lấy được nội dung");
    }

    return null;
}