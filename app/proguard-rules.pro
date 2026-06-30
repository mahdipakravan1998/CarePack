# CarePack release shrinker rules.
#
# The project uses AndroidX libraries that provide their own consumer rules.
# Keep this file intentionally small. Add a rule here only after reproducing
# a release-build failure that is fixed by the narrowest possible keep rule.

# Room schemas are exported at build time and committed under app/schemas.
# Do not keep Room implementation classes here unless a release-only failure
# proves that an additional rule is required.
