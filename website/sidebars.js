// @ts-check

/** @type {import('@docusaurus/plugin-content-docs').SidebarsConfig} */
const sidebars = {
  docs: [
    'intro',
    'installation',
    'quick-start',
    'route-composition',
    {
      type: 'category',
      label: 'Directives',
      items: [
        'directives/directives-route',
        'directives/directives-path',
        'directives/directives-method',
        'directives/directives-parameter',
        'directives/directives-form-field',
        'directives/directives-header',
        'directives/directives-marshalling',
        'directives/directives-io',
        'directives/directives-cookie',
        'directives/directives-security',
        'directives/directives-host-and-scheme',
        'directives/directives-respond-with',
        'directives/directives-file',
        'directives/directives-timeout',
        'directives/directives-basic',
        'directives/directives-misc',
        'directives/directives-websocket',
      ],
    },
    'rejection-handling',
    'exception-handling',
    'testing',
    'http4s-compat',
    'migration',
  ]
};

module.exports = sidebars;
