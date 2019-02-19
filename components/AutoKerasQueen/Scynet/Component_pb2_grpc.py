# Generated by the gRPC Python protocol compiler plugin. DO NOT EDIT!
import grpc

import Component_pb2 as Component__pb2
import Shared_pb2 as Shared__pb2


class ComponentStub(object):
  """*
  The Api whic is implemented by the different components.
  It is caled by the hatchery to instantiate the agents.
  """

  def __init__(self, channel):
    """Constructor.

    Args:
      channel: A grpc.Channel.
    """
    self.AgentStart = channel.unary_unary(
        '/Scynet.Component/AgentStart',
        request_serializer=Component__pb2.AgentStartRequest.SerializeToString,
        response_deserializer=Shared__pb2.Void.FromString,
        )
    self.AgentStop = channel.unary_unary(
        '/Scynet.Component/AgentStop',
        request_serializer=Component__pb2.AgentRequest.SerializeToString,
        response_deserializer=Shared__pb2.Void.FromString,
        )
    self.AgentStatus = channel.unary_unary(
        '/Scynet.Component/AgentStatus',
        request_serializer=Component__pb2.AgentRequest.SerializeToString,
        response_deserializer=Component__pb2.AgentStatusResponse.FromString,
        )
    self.AgentList = channel.unary_unary(
        '/Scynet.Component/AgentList',
        request_serializer=Component__pb2.AgentQuery.SerializeToString,
        response_deserializer=Component__pb2.ListOfAgents.FromString,
        )


class ComponentServicer(object):
  """*
  The Api whic is implemented by the different components.
  It is caled by the hatchery to instantiate the agents.
  """

  def AgentStart(self, request, context):
    """Start running a particular agent
    """
    context.set_code(grpc.StatusCode.UNIMPLEMENTED)
    context.set_details('Method not implemented!')
    raise NotImplementedError('Method not implemented!')

  def AgentStop(self, request, context):
    """Stop that agent
    """
    context.set_code(grpc.StatusCode.UNIMPLEMENTED)
    context.set_details('Method not implemented!')
    raise NotImplementedError('Method not implemented!')

  def AgentStatus(self, request, context):
    """Check the status of an agent.
    """
    context.set_code(grpc.StatusCode.UNIMPLEMENTED)
    context.set_details('Method not implemented!')
    raise NotImplementedError('Method not implemented!')

  def AgentList(self, request, context):
    """Retrieve a list of running agents.
    """
    context.set_code(grpc.StatusCode.UNIMPLEMENTED)
    context.set_details('Method not implemented!')
    raise NotImplementedError('Method not implemented!')


def add_ComponentServicer_to_server(servicer, server):
  rpc_method_handlers = {
      'AgentStart': grpc.unary_unary_rpc_method_handler(
          servicer.AgentStart,
          request_deserializer=Component__pb2.AgentStartRequest.FromString,
          response_serializer=Shared__pb2.Void.SerializeToString,
      ),
      'AgentStop': grpc.unary_unary_rpc_method_handler(
          servicer.AgentStop,
          request_deserializer=Component__pb2.AgentRequest.FromString,
          response_serializer=Shared__pb2.Void.SerializeToString,
      ),
      'AgentStatus': grpc.unary_unary_rpc_method_handler(
          servicer.AgentStatus,
          request_deserializer=Component__pb2.AgentRequest.FromString,
          response_serializer=Component__pb2.AgentStatusResponse.SerializeToString,
      ),
      'AgentList': grpc.unary_unary_rpc_method_handler(
          servicer.AgentList,
          request_deserializer=Component__pb2.AgentQuery.FromString,
          response_serializer=Component__pb2.ListOfAgents.SerializeToString,
      ),
  }
  generic_handler = grpc.method_handlers_generic_handler(
      'Scynet.Component', rpc_method_handlers)
  server.add_generic_rpc_handlers((generic_handler,))
