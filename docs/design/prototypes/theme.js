/* AWN 原型主题切换器：只改 data-theme 令牌，不动布局（docs/design/ui-system.md） */
(function () {
  var THEMES = [
    ["default", "默认·品牌金"],
    ["dark", "暗色"],
    ["cn-spring-festival", "春节"],
    ["cn-lantern", "元宵"],
    ["cn-dragon-boat", "端午"],
    ["cn-mid-autumn", "中秋"],
    ["cn-national-day", "国庆"],
    ["season-spring", "春"],
    ["season-summer", "夏"],
    ["season-autumn", "秋"],
    ["season-winter", "冬"]
  ];
  var saved = null;
  try { saved = localStorage.getItem("awn-proto-theme"); } catch (e) { /* 隐私模式忽略 */ }

  var picker = document.createElement("div");
  picker.className = "theme-picker";
  picker.innerHTML = '<div class="head">主题 / theme</div>';
  THEMES.forEach(function (t) {
    var b = document.createElement("button");
    b.type = "button";
    b.textContent = t[1];
    b.dataset.theme = t[0];
    b.setAttribute("aria-pressed", String(t[0] === (saved || "default")));
    b.addEventListener("click", function () {
      apply(t[0]);
      Array.prototype.forEach.call(picker.querySelectorAll("button"), function (x) {
        x.setAttribute("aria-pressed", String(x === b));
      });
    });
    picker.appendChild(b);
  });
  document.body.appendChild(picker);

  function apply(name) {
    document.documentElement.dataset.theme = name;
    try { localStorage.setItem("awn-proto-theme", name); } catch (e) { /* 忽略 */ }
  }
  if (saved) { apply(saved); }
})();
