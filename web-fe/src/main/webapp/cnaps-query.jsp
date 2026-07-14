<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!doctype html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>CNAPS 查询</title>
    <link rel="stylesheet" href="static/css/app.css">
</head>
<body>
<main class="app-shell">
    <nav class="top-nav">
        <a href="index.jsp">首页</a>
        <a href="cnaps-create.jsp">录入</a>
    </nav>
    <section class="workbench">
        <h1>凭证查询</h1>
        <form data-query-form>
            <label>状态 <input name="status" value="10_PENDING_REVIEW"></label>
            <label>流水号 <input name="serialNo"></label>
            <button type="submit">查询</button>
        </form>
        <pre id="result"></pre>
    </section>
</main>
<script src="static/js/cnaps.js"></script>
</body>
</html>
