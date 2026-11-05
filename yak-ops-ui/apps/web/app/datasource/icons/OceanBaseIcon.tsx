/**
 * OceanBase datasource icon.
 * SVG paths provided by the user and converted to a size-controlled React component.
 */
const OceanBaseIcon = ({
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
        d="M0 371.36v355.92s205.52 0.64 303.36-10.48c49.84-5.68 198.08-51.68 373.76-99.52 144.56-39.36 346.88-21.04 346.88-21.04V250s-234.64 2-308.16 10.32c-193.04 22-276.08 65.12-401.2 95.36C218.48 378.88 0 371.36 0 371.36z"
        fill="#0181FC"
      />
      <path
        d="M0 228.4v118.64s184.24-2.16 223.04-4.88c95.76-6.72 273.28-68.88 273.28-68.88l-35.68-108.16s-141.6 48.64-214.96 59.44C179.36 234.4 0 228.4 0 228.4z"
        fill="#FF9C00"
      />
      <path
        d="M1024 626.4v121.92s-162.48-4.96-232.56 3.76c-52.72 6.56-230.8 58.8-230.8 58.8L522.88 697.6s141.92-45.12 189.84-58.24C827.04 608.08 1024 626.4 1024 626.4z"
        fill="#0BC649"
      />
  </svg>
);

export default OceanBaseIcon;
