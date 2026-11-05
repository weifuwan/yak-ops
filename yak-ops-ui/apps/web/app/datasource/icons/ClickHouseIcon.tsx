/**
 * ClickHouse datasource icon.
 * SVG paths provided by the user and converted to a size-controlled React component.
 */
const ClickHouseIcon = ({
  height = "24",
  width = "24",
}: {
  width?: string | number;
  height?: string | number;
}) => (
  <svg
    viewBox="0 0 1160 1024"
    xmlns="http://www.w3.org/2000/svg"
    width={width}
    height={height}
    preserveAspectRatio="xMidYMid meet"
    aria-hidden="true"
    style={{ display: "block", flexShrink: 0 }}
  >
      <path
        d="M0 0h122.802644v901.197356H0zM259.142587 0H381.945231v1024H259.142587zM518.76865 0h122.802644v1024h-122.802644zM777.911237 0h122.802644v1024h-122.802644zM1037.537299 409.503305H1160.339943v204.99339h-122.802644z"
        fill="#FFCB14"
      />
      <path
        d="M0 901.197356h122.802644v122.802644H0z"
        fill="#FF0000"
      />
  </svg>
);

export default ClickHouseIcon;
