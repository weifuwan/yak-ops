export default [
  {
    name: "Login",
    path: "/login",
    component: "./login",
    layout: false,
    hideInMenu: true,
  },
  {
    path: "/",
    layout: false,
    component: "@/layouts/SiteLayout",
    routes: [
      {
        path: "/",
        redirect: "/data-source",
      },
      {
        name: "数据源管理",
        path: "/data-source",
        component: "./data-source",
        access: "isAuthenticated",
      },
      {
        path: "*",
        redirect: "/data-source",
      },
    ],
  },
];
