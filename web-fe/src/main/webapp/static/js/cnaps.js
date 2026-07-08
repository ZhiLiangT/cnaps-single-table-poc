const result = document.querySelector("#result");

const headers = () => ({
  "Content-Type": "application/json",
  requestId: `REQ-${Date.now()}`,
  operatorNo: "77210021",
  branchNo: "772",
  workDate: new Date().toISOString().slice(0, 10)
});

const show = async (response) => {
  const payload = await response.json();
  result.textContent = JSON.stringify(payload, null, 2);
};

document.querySelector("[data-health]")?.addEventListener("click", async () => {
  await show(await fetch("api/health"));
});

document.querySelector("[data-create-form]")?.addEventListener("submit", async (event) => {
  event.preventDefault();
  const body = Object.fromEntries(new FormData(event.currentTarget).entries());
  await show(await fetch("api/cnaps/vouchers", {
    method: "POST",
    headers: headers(),
    body: JSON.stringify(body)
  }));
});

document.querySelector("[data-query-form]")?.addEventListener("submit", async (event) => {
  event.preventDefault();
  const params = new URLSearchParams(new FormData(event.currentTarget));
  await show(await fetch(`api/cnaps/vouchers?${params}`, { headers: headers() }));
});

document.querySelector("[data-review-form]")?.addEventListener("submit", async (event) => {
  event.preventDefault();
  const form = Object.fromEntries(new FormData(event.currentTarget).entries());
  await show(await fetch(`api/cnaps/vouchers/${form.billId}/review-pass`, {
    method: "POST",
    headers: headers(),
    body: JSON.stringify({ reviewComment: form.reviewComment })
  }));
});
