// NURTURE Workshop —— 交互演示脚本（涟漪 / Toast / Tabs / Loading）
// 原则：动效只做增强；脚本失败时页面内容完全可用。
(function () {
  "use strict";
  var reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  // ---- 苔青涟漪：雨滴落进池塘（transform: scale + opacity, ≤400ms） ----
  document.addEventListener("click", function (ev) {
    var btn = ev.target.closest(".btn");
    if (!btn || btn.disabled || btn.classList.contains("is-disabled")) return;
    if (reduceMotion) return;
    var rect = btn.getBoundingClientRect();
    var d = Math.max(rect.width, rect.height);
    var s = document.createElement("span");
    s.className = "ripple";
    s.style.width = s.style.height = d + "px";
    s.style.left = (ev.clientX - rect.left - d / 2) + "px";
    s.style.top = (ev.clientY - rect.top - d / 2) + "px";
    btn.appendChild(s);
    s.addEventListener("animationend", function () { s.remove(); });
  });

  // ---- Toast 演示 ----
  var stack = document.querySelector(".toast-stack");
  function toast(type, title, body) {
    if (!stack) return;
    var t = document.createElement("div");
    t.className = "toast" + (type ? " toast-" + type : "");
    t.setAttribute("role", "status");
    t.innerHTML = '<div><div class="toast-title"></div><div class="toast-body"></div></div>';
    t.querySelector(".toast-title").textContent = title;
    t.querySelector(".toast-body").textContent = body;
    stack.appendChild(t);
    setTimeout(function () { t.remove(); }, 4200);
  }
  document.querySelectorAll("[data-toast]").forEach(function (el) {
    el.addEventListener("click", function () {
      toast(el.getAttribute("data-toast"), el.getAttribute("data-toast-title") || "操作成功", el.getAttribute("data-toast-body") || "");
    });
  });

  // ---- Loading 按钮演示（2s 后恢复并给出成功反馈） ----
  document.querySelectorAll("[data-loading-demo]").forEach(function (el) {
    el.addEventListener("click", function () {
      if (el.classList.contains("is-loading")) return;
      el.classList.add("is-loading");
      el.setAttribute("aria-busy", "true");
      setTimeout(function () {
        el.classList.remove("is-loading");
        el.removeAttribute("aria-busy");
        toast("success", "已提交", "预约申请已发送给导师，结果会第一时间通知你。");
      }, 1600);
    });
  });

  // ---- Tabs 演示 ----
  document.querySelectorAll(".tabs").forEach(function (tabs) {
    var tabList = Array.prototype.slice.call(tabs.querySelectorAll(".tab"));
    tabList.forEach(function (tab) {
      tab.addEventListener("click", function () {
        tabList.forEach(function (t) { t.setAttribute("aria-selected", "false"); });
        tab.setAttribute("aria-selected", "true");
        var panelId = tab.getAttribute("data-panel");
        if (!panelId) return;
        var scope = tabs.parentElement;
        scope.querySelectorAll(".tab-panel").forEach(function (p) {
          p.hidden = p.id !== panelId;
        });
      });
    });
  });
})();
