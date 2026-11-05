/**
 * SSH datasource icon.
 * SVG paths provided by the user and converted to a size-controlled React component.
 */
const SshIcon = ({
  height = "24",
  width = "24",
}: {
  width?: string | number;
  height?: string | number;
}) => (
  <svg
    viewBox="0 0 1024 1024"
    xmlns="http://www.w3.org/2000/svg"
    width={width}
    height={height}
    preserveAspectRatio="xMidYMid meet"
    aria-hidden="true"
    style={{ display: "block", flexShrink: 0 }}
  >
    <path
      d="M864 96a64 64 0 0 1 64 64v704a64 64 0 0 1-64 64h-704a64 64 0 0 1-64-64v-704a64 64 0 0 1 64-64h704z m-12.8 64H172.8a12.8 12.8 0 0 0-12.8 12.8v678.4c0 7.04 5.76 12.8 12.8 12.8h678.4c7.04 0 12.8-5.76 12.8-12.8V172.8a12.8 12.8 0 0 0-12.8-12.8z"
      fill="#000000"
    />
    <path
      d="M582.4 704h179.2a6.4 6.4 0 0 0 6.4-6.4v-51.2a6.4 6.4 0 0 0-6.4-6.4H582.4a6.4 6.4 0 0 0-6.4 6.4v51.2a6.4 6.4 0 0 0 6.4 6.4zM350.528 321.472l183.68 183.68a6.4 6.4 0 0 1 0 9.088l-183.68 183.68a12.8 12.8 0 0 1-9.024 3.84h-72.96a3.2 3.2 0 0 1-2.24-5.504L451.2 512a3.2 3.2 0 0 0 0-4.48L266.24 323.2a3.2 3.2 0 0 1 2.304-5.44h72.96a12.8 12.8 0 0 1 9.024 3.776z"
      fill="#000000"
    />
  </svg>
);

export default SshIcon;
