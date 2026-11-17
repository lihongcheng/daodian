(function(){
  const paths = {
    clock:'<circle cx="12" cy="12" r="8.7"/><path d="M12 7v5l3 2"/>',
    sun:'<circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.4 1.4m11.2 11.2L19 19M5 19l1.4-1.4M17.6 6.4 19 5"/>',
    sunset:'<path d="M3 16h18M5 20h14M7 13a5 5 0 0 1 10 0M12 2v4M4 8l2 2m14-2-2 2"/>',
    check:'<path d="m5 12 4 4L19 6"/>',
    checkCircle:'<circle cx="12" cy="12" r="9"/><path d="m7 12 3 3 7-7"/>',
    grid:'<rect x="3" y="3" width="7" height="7" rx="1.2"/><rect x="14" y="3" width="7" height="7" rx="1.2"/><rect x="3" y="14" width="7" height="7" rx="1.2"/><rect x="14" y="14" width="7" height="7" rx="1.2"/>',
    moon:'<path d="M20.4 14.2A8.8 8.8 0 0 1 9.8 3.6a8.8 8.8 0 1 0 10.6 10.6Z"/>',
    refresh:'<path d="M20 7v5h-5M4 17v-5h5"/><path d="M6.1 7a7 7 0 0 1 12-1L20 12M4 12l1.9 6a7 7 0 0 0 12-1"/>',
    smartphone:'<rect x="6" y="2" width="12" height="20" rx="2"/><path d="M10 5h4M11 19h2"/>',
    shield:'<path d="m12 3 8 3v6c0 4-4 7-8 9-4-2-8-5-8-9V6Z"/><path d="m8 12 3 3 5-6"/>',
    alarm:'<circle cx="12" cy="13" r="7"/><path d="M12 9v4l3 2M3 4l3-2m12 0 3 2M7 20l-1 2m11-2 1 2"/>',
    battery:'<rect x="3" y="6" width="16" height="12" rx="2"/><path d="M22 10v4M7 10h8v4H7z"/>',
    bell:'<path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4"/>',
    boot:'<path d="M12 2v10M6 5a9 9 0 1 0 12 0"/>',
    arrowLeft:'<path d="m12 5-7 7 7 7M5 12h15"/>',
    chevronRight:'<path d="m9 5 7 7-7 7"/>',
    chevronDown:'<path d="m5 9 7 7 7-7"/>',
    settings:'<path d="m10 2-1 3-3 1-3-1-1 4 3 2v3l-3 2 2 4 3-1 3 2 1 3h4l1-3 3-2 3 1 2-4-3-2v-3l3-2-1-4-3 1-3-1-1-3Z"/><circle cx="12" cy="12" r="3"/>',
    calendar:'<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M7 2v6m10-6v6M3 11h18M7 15h2m6 0h2M7 18h2"/>',
    mail:'<rect x="3" y="5" width="18" height="14" rx="2"/><path d="m3 6 9 7 9-7"/>',
    clipboard:'<rect x="5" y="4" width="14" height="17" rx="2"/><rect x="8" y="2" width="8" height="4" rx="1"/><path d="M9 11h6m-6 4h6"/>',
    play:'<path d="m8 4 12 8-12 8Z"/>',
    stop:'<rect x="6" y="6" width="12" height="12" rx="1"/>',
    photo:'<path d="M3 7h4l2-3h6l2 3h4v13H3Z"/><circle cx="12" cy="13" r="4"/>',
    alert:'<path d="m12 3 10 18H2Z"/><path d="M12 9v5m0 3v.1"/>',
    info:'<circle cx="12" cy="12" r="9"/><path d="M12 11v6m0-10v.1"/>',
    external:'<path d="M14 3h7v7m0-7L10 14M10 4H4v16h16v-6"/>',
    file:'<path d="M5 2h9l5 5v15H5Z"/><path d="M14 2v6h5M9 12h6m-6 4h6"/>',
    eye:'<path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7-10-7-10-7Z"/><circle cx="12" cy="12" r="3"/>',
    wifi:'<path d="M3 7a15 15 0 0 1 18 0M6 11a10 10 0 0 1 12 0m-9 4a5 5 0 0 1 6 0m-3 4v.1"/>',
    pin:'<path d="M19 10c0 6-7 11-7 11S5 16 5 10a7 7 0 1 1 14 0Z"/><circle cx="12" cy="10" r="2.5"/>',
    signal:'<path d="M4 19v-3m5 3v-7m5 7V8m5 11V4"/>',
    search:'<circle cx="10" cy="10" r="6.5"/><path d="m15 15 6 6"/>',
    pause:'<path d="M8 4v16M16 4v16"/>',
    close:'<path d="m6 6 12 12M6 18 18 6"/>'
  };
  function icon(name, small) { return '<svg class="icon'+(small?' icon-sm':'')+'" viewBox="0 0 24 24" aria-hidden="true">'+(paths[name]||paths.clock)+'</svg>'; }

window.Icons={icon};
})();
