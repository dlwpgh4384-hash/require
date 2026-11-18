import { WithdrawalSharedRegion } from "./WithdrawalSharedRegion";

export function StaffingBoardPage({ developerId }: { developerId?: string }) {
  return (
    <section
      className="screen-body withdrawal-page"
      data-testid="staffing-board-page"
      data-screen-id="UI-28"
    >
      <WithdrawalSharedRegion screenId="UI-28" developerId={developerId} />
    </section>
  );
}
