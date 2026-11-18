import { WithdrawalSharedRegion } from "./WithdrawalSharedRegion";

export function OpportunityPage({ opportunityId }: { opportunityId?: string }) {
  return (
    <section
      className="screen-body withdrawal-page"
      data-testid="opportunity-page"
      data-screen-id="UI-29"
    >
      <WithdrawalSharedRegion screenId="UI-29" opportunityId={opportunityId} />
    </section>
  );
}
