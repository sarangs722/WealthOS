# Quick entry and concise forms

Usability refinement of the existing native application, schema 7.

- Default investment entry: name and current value; INR and today preselected. Other figures optional. Alternative units x NAV uses HALF_EVEN at currency scale. Never derive units from a rounded total.
- Dated reported invested amount is not FIFO basis, cash flow or annualised return. Missing values stay missing.
- Explicitly start unit tracking on the same holding from a known date, preserving earlier value snapshots and creating no cash movement.
- Shared compact forms with fixed save action and optional details. Review bank, retirement/property, deposits/loans, income and insurance.
- Explicit encrypted Room 6-to-7 migration and portable schema-6 backup migration.
- Verify mathematics, persistence/atomicity, migrations, backups, native workflows, normal/large fonts and dark theme. Build and lint.

Sources: [Groww tracker](https://groww.in/track) exposes invested/current amounts; [Groww NAV guidance](https://groww.in/help/mutual-funds/mf-dashboard/my-mutual-funds-nav-is-not-updated) explains prior-day NAV. Users can choose the financial date of copied figures. No data connector is added.
