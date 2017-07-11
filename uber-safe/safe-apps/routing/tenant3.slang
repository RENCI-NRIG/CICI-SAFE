defenv Selfie() :- 
  spec('Load the principal keypair'),
  principal($1).
  (* principal('/home/qiang/Desktop/safe-new-version-benchmark/safe/safe-apps/routing/keys/tenant.pem'). *)

defenv IANA :- u'jSvdyfIYzmWO-d1V_mJqPq1fPkIS8CPkE2TxFfsk3ow'.  (* The owner of the entire IP space *)
defenv IPUniverse :- 'jSvdyfIYzmWO-d1V_mJqPq1fPkIS8CPkE2TxFfsk3ow:0/0'. (* The entire space *)

defcon makeMyIdentitySet(?CN) :-
  spec('Construct an identity set for self'),
  {
    principal($SelfKey) :- spec("identity set for $CN").
    name('').
  }.

defcon addLink2IDSet(?LinkRef) :-
  spec("Add a link to the tenant's ID set"),
  {
    link($LinkRef).
    name('').
  }.

defcon delegateSubPrefix(?SubPrefix, ?Tenant, ?TenantPrefix) :-
  spec("Delegate a sub-prefix to a tenant; the prefix is mapped to the tenant's IP space"),
  {
    delegatePrefix($SubPrefix, $Tenant, $TenantPrefix).  (* $SubPrefix and $TenantPrefix should be of the same size. *)
    name('').                   (* Store this token to the id set of the tenant *)
  }.

defcon PrefixRules() :-
  spec("Rules for inferring the control of an IP prefix"),
  {
    controlPrefix(?Tenant, ?Prefix) :- certifiedControl($IANA, $IPUniverse, ?Tenant, ?Prefix).
 
    certifiedControl(?AnchorOwner, ?AnchorPrefix, ?Tenant, ?Prefix) :- 
      ?AnchorOwner: delegatePrefix(?SubAPrefix, ?Tenant, ?TenantPrefix),  (* Check the sizes of ?SubAPrefix and ?TenantPrefix *) 
      contain(?AnchorPrefix, ?SubAPrefix),
      contain(?TenantPrefix, ?Prefix).
  
    (* Top-down. Slightly less efficient.
    certifiedControl(?AnchorOwner, ?AnchorPrefix, ?Tenant, ?Prefix) :-
      ?AnchorOwner: delegatePrefix(?SubAPrefix, ?T, ?TPrefix),
      contain(?AnchorPrefix, ?SubAPrefix),
      certifiedControl(?T, ?TPrefix, ?Tenant, ?Prefix).
    *)

    (* Bottom-up. *)
    certifiedControl(?AnchorOwner, ?AnchorPrefix, ?Tenant, ?Prefix) :-
      ?SomeTenant: delegatePrefix(?SomePrefix, ?Tenant, ?TenantPrefix),
      contain(?TenantPrefix, ?Prefix),
      certifiedControl(?AnchorOwner, ?AnchorPrefix, ?SomeTenant, ?SomePrefix).
 }.

defetch fetchCert(?LinkRef) :- [?LinkRef].

defguard queryControl(?Tenant, ?Prefix) :-
  spec("Query if ?Tenant controls ?Prefix"),
  ?NetIDChainRef := fetchCert($Tenant),
  ?PrefixRulesRef := PrefixRules(),
  {
    import($NetIDChainRef).
    import($PrefixRulesRef).
    controlPrefix($Tenant, $Prefix)?
  }.

defpost postIdSet(?CN) :- [makeMyIdentitySet(?CN)].

defpost postLinkToIDSet(?LinkRef) :- [addLink2IDSet(?LinkRef)].

defpost postPrefixDelegation(?SubPrefix, ?Tenant, ?TenantPrefix) :- [delegateSubPrefix(?SubPrefix, ?Tenant, ?TenantPrefix)].
