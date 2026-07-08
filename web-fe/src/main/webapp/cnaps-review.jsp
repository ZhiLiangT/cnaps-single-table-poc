<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!doctype html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>CNAPS 复核</title>
    <link rel="stylesheet" href="static/css/app.css">
</head>
<body>
<main class="app-shell">
    <nav class="top-nav">
        <a href="index.jsp">首页</a>
        <a href="cnaps-create.jsp">录入</a>
        <a href="cnaps-query.jsp">查询</a>
    </nav>
    <section class="workbench">
        <h1>复核处理</h1>
        <form data-review-form>
            <label>凭证编号 <input name="billId" required></label>
            <label>复核意见 <input name="reviewComment" value="复核通过"></label>
            <button type="submit">通过</button>
        </form>
        <pre id="result"></pre>
    </section>
</main>
<script src="static/js/cnaps.js"></script>
</body>
</html>
