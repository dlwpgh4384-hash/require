import { WithdrawalSharedRegion } from "./WithdrawalSharedRegion";

export function WorkforceForecastPage({
  developerId,
}: {
  developerId?: string;
}) {
  return (
    <section
      className="screen-body withdrawal-page"
      data-testid="workforce-forecast-page"
      data-screen-id="UI-17"
    >
      <WithdrawalSharedRegion screenId="UI-17" developerId={developerId} />
    </section>
  );
}
