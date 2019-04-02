﻿using System;
using System.Collections.Generic;
using System.Linq;
using System.Net.Mime;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using Confluent.Kafka;
using Google.Protobuf;
using Grpc.Core;
using Orleans;
using Microsoft.Extensions.Logging;
using Scynet.GrainInterfaces.Facade;
using Scynet.GrainInterfaces.Registry;
using Scynet.GrainInterfaces.Agent;

namespace Scynet.HatcheryFacade.RPC
{
    public class SubscriberClient : IFacade
    {
        private readonly ILogger _logger;
        private readonly IEnumerable<string> _brokers;

        private readonly IClusterClient ClusterClient;
        private readonly Guid FacadeGuid = new Guid();
        private Timer Timer;

        private Dictionary<string, CancellationTokenSource> cancellationTokens = new Dictionary<string, CancellationTokenSource>();
        private Dictionary<string, Channel> channels = new Dictionary<string, Channel>();

        public SubscriberClient(ILogger<SubscriberClient> logger, IEnumerable<String> brokers, IClusterClient clusterClient)
        {
            _logger = logger;
            _brokers = brokers;
            ClusterClient = clusterClient;

            Register();
        }

        public async void Register()
        {
            var facadeWrap = await ClusterClient.CreateObjectReference<IFacade>(this);

            var registry = ClusterClient.GetGrain<IRegistry<Guid, FacadeInfo>>(0);

            Timer = new Timer(async _ =>
            {
                await registry.Register(FacadeGuid, new FacadeInfo()
                {
                    Facade = facadeWrap,
                    LastUpdate = DateTime.Now,
                });
            }, null, TimeSpan.FromSeconds(0), TimeSpan.FromSeconds(30));
        }

        public async void Start(IExternalAgent agent)
        {
            Subscribe(await agent.GetAddress(), agent.GetPrimaryKey().ToString());
        }

        public async void Stop(IExternalAgent agent)
        {
            Unsubscribe(await agent.GetAddress(), agent.GetPrimaryKey().ToString());
        }

        public void Subscribe(string address, string agentId)
        {
            if (cancellationTokens.ContainsKey(address + "/" + agentId))
            {
                return;
            }
            if (!channels.ContainsKey(address))
            {
                channels[address] = new Channel(address, ChannelCredentials.Insecure);
            }
            var cts = new CancellationTokenSource();
            cancellationTokens[address + "/" + agentId] = cts;

            var client = new Subscriber.SubscriberClient(channels[address]);
            var token = cts.Token;
            var producer = new Producer<string, byte[]>(new Handle());
            var subscriptionThread = new Thread(async () =>
            {
                var subscriptionId = new Guid().ToString();

                while (true)
                {
                    token.ThrowIfCancellationRequested();
                    using (var pull = client.StreamingPull(
                        new StreamingPullRequest() { Id = subscriptionId }, null, null, token
                        ))
                    {
                        while (await pull.ResponseStream.MoveNext(token))
                        {
                            var message = pull.ResponseStream.Current.Message;

                            await producer.ProduceAsync(agentId, new Message<string, byte[]>()
                            {
                                Key = message.PartitionKey,
                                Value = message.Data.ToByteArray(),
                                Timestamp = new Timestamp((long)message.Key, TimestampType.CreateTime),
                            }, token);

                            await client.AcknowledgeAsync(new AcknowledgeRequest()
                            {
                                Id = subscriptionId,
                                AcknowledgeMessage = message.Index,
                                Partition = message.Partition,
                            }, null, null, token);
                        }
                    }
                }
            });
            subscriptionThread.Start();
        }

        public void Unsubscribe(string address, string agentId)
        {
            cancellationTokens[address + "/" + agentId].Cancel();
            cancellationTokens[address + "/" + agentId].Dispose();
            cancellationTokens.Remove(address + "/" + agentId);
        }
    }
}
