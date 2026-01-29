// Simple client-side i18n toggle for EN / KO
(function(){
  var LANG_KEY = 'rr_lang';

  function getCurrentLang() {
    try {
      var stored = localStorage.getItem(LANG_KEY);
      if (stored === 'en' || stored === 'ko') return stored;
    } catch (e) {}
    return 'ko';
  }

  function applyLang(lang) {
    try {
      document.documentElement.setAttribute('data-lang', lang);
    } catch (e) {}

    var nodes = document.querySelectorAll('[data-i18n-en][data-i18n-ko]');
    nodes.forEach(function (el) {
      var text = (lang === 'en') ? el.getAttribute('data-i18n-en') : el.getAttribute('data-i18n-ko');
      if (text != null) el.textContent = text;
    });

    var toggles = document.querySelectorAll('[data-lang-toggle]');
    toggles.forEach(function (btn) {
      var v = btn.getAttribute('data-lang-toggle');
      if (!v) return;
      if (v === lang) btn.classList.add('active');
      else btn.classList.remove('active');
    });
  }

  function setLang(lang) {
    if (lang !== 'en' && lang !== 'ko') return;
    try { localStorage.setItem(LANG_KEY, lang); } catch (e) {}
    applyLang(lang);
  }

  window.RecordRoomI18n = {
    setLang: setLang,
    getLang: getCurrentLang
  };

  document.addEventListener('DOMContentLoaded', function () {
    var lang = getCurrentLang();
    applyLang(lang);

    document.body.addEventListener('click', function (e) {
      var t = e.target.closest('[data-lang-toggle]');
      if (!t) return;
      e.preventDefault();
      var v = t.getAttribute('data-lang-toggle');
      if (!v) return;
      setLang(v);
    });
  });
})();
