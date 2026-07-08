<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!doctype html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>CNAPS 录入</title>
    <link rel="stylesheet" href="static/css/app.css">
</head>
<body>
<main class="app-shell">
    <nav class="top-nav">
        <a href="index.jsp">首页</a>
        <a href="cnaps-query.jsp">查询</a>
        <a href="cnaps-review.jsp">复核</a>
    </nav>
    <form class="workbench" data-create-form>
        <h1>凭证录入</h1>
        <div class="grid">
            <label>收款账号 <input name="payeeAccountNo" required maxlength="64" value="622200000000000001"></label>
            <label>收款人 <input name="payeeName" required maxlength="128" value="收款人名称"></label>
            <label>金额 <input name="amount" required pattern="^[0-9]+(\.[0-9]{1,2})?$" value="5600.00"></label>
            <label>业务类型 <input name="businessType" required value="02102"></label>
            <label>优先级 <input name="priority" required value="NORM"></label>
            <label>系统类型 <input name="systemType" required value="CNAPS"></label>
        </div>
        <input type="hidden" name="accountPart1" value="404045">
        <input type="hidden" name="accountPart2" value="00772">
        <input type="hidden" name="accountPart3" value="000000000001">
        <input type="hidden" name="debitMode" value="1">
        <input type="hidden" name="feeChargeMode" value="1">
        <input type="hidden" name="sendMode" value="0">
        <input type="hidden" name="faxFlag" value="0">
        <button type="submit">提交</button>
        <pre id="result"></pre>
    </form>
</main>
<script src="static/js/cnaps.js"></script>
</body>
</html>
