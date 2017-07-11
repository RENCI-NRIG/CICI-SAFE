defenv VMLevel :- "1".
defenv IaaS :- "___iaas".

defcon whiteList() :- 
  {
    acceptGit("Z2l0Oi8vZ2l0aHViLmNvbS9ib290MmRvY2tlci9ib290MmRvY2tlcg==", "628996af581132dfdaf1a475981cdfe57e5de48c", "a735f177b26c580e16e0ce22c9e31922", "1484985484942329"). 
    label("git-whitelist").
  }.

definit whiteList().

defcon principalPolicySet() :-
  {
    acceptVM(?IP, ?Protocol, ?Port) :- 
      $IaaS: attestTarget(?PrincipalName, $VMLevel, ?NetSpace, ?IP, ?Protocol, ?Port),
      $IaaS: instanceFact(?PrincipalName, ?ImageID, ?CloudContainerConfID),
      $IaaS: cloudContainerConf(?CloudContainerConfID, ?CCConfPolicyHash, ?CCConfTemplate, ?CCConfTempArgs),
      acceptImageProducingChain(?ImageID),
      acceptImageByProperty(?ImageID).

    acceptImageProducingChain(?ImageID) :-
      $IaaS: initialImageFact(?ImageID).

    acceptImageProducingChain(?ImageID) :-
      $IaaS: vmImageProducer(?ImageID, ?ProducerImageID, ?CloudContainerConfID),
      acceptImageProducingChain(?ProducerImageID).

    acceptImageByProperty(?ImageID) :-
      ?Speaker: imageFact(?ImageID, ?Git, ?Revision, ?Checksum, ?Timestamp),  // ?Speaker should be image builder
      acceptGit(?Git, ?Revision, ?Checksum, ?Timestamp).

    label("principal-policy").
  }.

definit principalPolicySet().

defcon attestationAssertions() :- 
  {
    "___iaas":attestTarget("8fa2a3d9-6156-4fff-9054-a1189d4e4a4f", 1, "default", "10.10.1.49", "tcp", 3000).
    "___iaas":vmImageProducer("593c0e90-655e-471c-880d-07879a5cf316", "722985a3-8409-49c5-b183-394c8925e4b9", "16").
    "___iaas":cloudContainerConf("16", "eyJJbnN0YW5jZUlwVXNhZ2UiOiAidHJ1ZSIsICJJbnN0YW5jZUFsbG93ZWRBY3Rpb25zIjogImV2ZW50ID09IFwiRmlwRGV0YWNoUHJlXCIgb3IgZXZlbnQgPT0gXCJJbnN0YW5jZUNvbnNvbGVTaG93UHJlXCIiLCAiSW5zdGFuY2VWb2x1bWVVc2FnZSI6ICJ0cnVlIiwgIkluc3RhbmNlTmV0d29ya1VzYWdlIjogImZhbHNlIiwgIk5ldHdvcmtVc2FnZSI6ICJjYWxsYmFjayBkZWZpbmVfcmVhY2hhYmxlIGNvbnRhaW5lciBjb250YWluZXJfc2VjdXJpdHlfZ3JvdXAgXCIwLjAuMC4wLzA6MTo2NTUzNTp0Y3BcIiBhbmQgY2FsbGJhY2sgZGVmaW5lX3JlYWNoYWJsZSBjb250YWluZXIgY29udGFpbmVyX3NlY3VyaXR5X2dyb3VwIFwiMC4wLjAuMC8wOjE6NjU1MzU6dWRwXCIgYW5kIGNhbGxiYWNrIGRlZmluZV9jb250YWluZXJfaW1hZ2VzIGNvbnRhaW5lciBcIjcyMjk4NWEzLTg0MDktNDljNS1iMTgzLTM5NGM4OTI1ZTRiOVwiIn0=", "9c10f084421448178bcf9eecd02a7ca8@tapcon-default", "eyJpbWFnZSI6ICI3MjI5ODVhMy04NDA5LTQ5YzUtYjE4My0zOTRjODkyNWU0YjkifQ==").
    "___iaas":initialImageFact("722985a3-8409-49c5-b183-394c8925e4b9").
    "___iaas":instanceFact("8fa2a3d9-6156-4fff-9054-a1189d4e4a4f", "593c0e90-655e-471c-880d-07879a5cf316", "16").
    "1097e42f-6704-41e3-be28-33dfa0a835f8":imageFact("593c0e90-655e-471c-880d-07879a5cf316", "Z2l0Oi8vZ2l0aHViLmNvbS9ib290MmRvY2tlci9ib290MmRvY2tlcg==", "628996af581132dfdaf1a475981cdfe57e5de48c", "a735f177b26c580e16e0ce22c9e31922", "1484985484942329").

    label("attestation-assertions").
  }.
  
definit attestationAssertions(). 

defguard checkTarget(?IP, ?Port, ?Protocol) :-
  ?WhiteListSet := label("git-whitelist"),
  ?PolicySet := label("principal-policy"),
  ?FactsSet := label("attestation-assertions"), 
  {
    link($WhiteListSet).
    link($PolicySet).
    link($FactsSet).
    acceptVM($IP, $Port, $Protocol)?
  }.
