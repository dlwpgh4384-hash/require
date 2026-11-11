import { NavigationRoot } from "../../app/NavigationRoot";
import { PlaceholderScreen } from "./PlaceholderScreen";

export function CommonNavigationPage() {
  return (
    <NavigationRoot
      renderScreen={(scrnId) => <PlaceholderScreen scrnId={scrnId} />}
    />
  );
}
