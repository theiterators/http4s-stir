// @ts-check
// Note: type annotations allow type checking and IDEs autocompletion

const lightCodeTheme = require('prism-react-renderer/themes/github');
const darkCodeTheme = require('prism-react-renderer/themes/dracula');

const projectTitle = 'http4s-stir';
const organizationName = 'theiterators';
const editDocsUrl = 'https://github.com/theiterators/http4s-stir/tree/master/docs/';
const docsPath = '../docs';
const navbarTitle = 'http4s-stir';
const projectGitHubUrl = 'https://github.com/theiterators/http4s-stir';

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'http4s-stir',
  tagline: 'Pekko HTTP (Akka HTTP) style DSL directives for http4s with cats-effect IO',
  url: 'https://theiterators.github.io',
  baseUrl: '/http4s-stir/',
  trailingSlash: false,
  onBrokenLinks: 'throw',
  onBrokenMarkdownLinks: 'warn',
  favicon: 'img/favicon.ico',
  organizationName: organizationName,
  projectName: 'http4s-stir',

  presets: [
    [
      'classic',
      /** @type {import('@docusaurus/preset-classic').Options} */
      ({
        docs: {
          routeBasePath: '/',
          sidebarPath: require.resolve('./sidebars.js'),
          path: docsPath,
          editUrl: editDocsUrl,
          showLastUpdateAuthor: false,
          showLastUpdateTime: false,
        },
        blog: false,
        pages: false,
        theme: {
          customCss: require.resolve('./src/css/custom.css'),
        },
      }),
    ],
  ],
  themeConfig:
    /** @type {import('@docusaurus/preset-classic').ThemeConfig} */
    ({
      navbar: {
        title: navbarTitle,
        logo: {
          alt: 'Logo',
          src: 'img/favicon.ico',
        },
        items: [
          {
            to: '/',
            position: 'left',
            label: 'Documentation',
          },
          {
            href: projectGitHubUrl,
            label: 'GitHub',
            position: 'right',
          },
        ],
      },
      sidebar: {
        hideable: true,
      },
      footer: {
        style: 'dark',
        links: [
          {
            title: 'Docs',
            items: [
              {
                label: 'Introduction',
                to: '/',
              },
            ],
          },
          {
            title: 'Community',
            items: [
              {
                label: 'Twitter',
                href: 'https://twitter.com/iteratorshq',
              },
              {
                label: 'GitHub',
                href: projectGitHubUrl,
              }
            ],
          },
        ],
        copyright: `Copyright \u00a9 ${new Date().getFullYear()} <a href="https://www.iteratorshq.com">Iterators</a> sp. z o.o.`,
      },
      prism: {
        theme: lightCodeTheme,
        darkTheme: darkCodeTheme,
        additionalLanguages: ['java','scala']
      },
    }),
};

module.exports = config;
