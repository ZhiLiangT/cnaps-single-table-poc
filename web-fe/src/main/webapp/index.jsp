<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!doctype html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>CNAPS WebFE</title>
    <link rel="stylesheet" href="static/css/app.css">
</head>
<body>
<main class="app-shell">
    <nav class="top-nav">
        <a href="cnaps-create.jsp">录入</a>
        <a href="cnaps-query.jsp">查询</a>
        <a href="cnaps-review.jsp">复核</a>
    </nav>
    <section class="workbench">
        <h1>CNAPS 往账凭证</h1>
        <div class="actions">
            <button type="button" data-health>健康检查</button>
        </div>
        <pre id="result"></pre>
    </section>
</main>
<script src="static/js/cnaps.js"></script>
</body>
</html>
