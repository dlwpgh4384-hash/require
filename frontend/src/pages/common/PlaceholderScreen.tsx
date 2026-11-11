import { NavigationState } from "../../shared/layout/NavigationState";

export interface PlaceholderScreenProps {
  scrnId: string;
}

export function PlaceholderScreen({ scrnId }: PlaceholderScreenProps) {
  return (
    <section data-testid={scrnId.toLowerCase()} aria-label={`${scrnId} 본문`}>
      <NavigationState kind="ready" />
    </section>
  );
}
