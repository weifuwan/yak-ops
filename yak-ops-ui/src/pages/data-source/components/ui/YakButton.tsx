import { Button, type ButtonProps } from 'antd';
import './YakButton.less';

export type YakButtonProps = ButtonProps & {
  /**
   * Keep icon-only actions aligned to the same square rhythm as normal buttons
   * without forcing Ant Design's circular shape.
   */
  iconOnly?: boolean;

  /**
   * Optional visual treatment.
   *
   * glass:
   * Transparent by default, revealing a subtle translucent surface on hover.
   */
  effect?: 'default' | 'glass';
};

export default function YakButton({
  className,
  iconOnly = false,
  effect = 'default',
  ...props
}: YakButtonProps) {
  return (
    <Button
      {...props}
      className={[
        'yak-button',
        iconOnly ? 'yak-button--icon-only' : '',
        effect === 'glass' ? 'yak-button--glass' : '',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    />
  );
}